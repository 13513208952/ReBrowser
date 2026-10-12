package io.github.reoutlook;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Sole owner of ReBrowser workspace collections, active selection, and metadata persistence. */
final class ReBrowserWorkspaceController {
    static final class TabCloseResult {
        final boolean valid;
        final boolean resetOnly;
        final ReBrowserStore.Tab nextActiveTab;

        private TabCloseResult(
                boolean valid,
                boolean resetOnly,
                ReBrowserStore.Tab nextActiveTab
        ) {
            this.valid = valid;
            this.resetOnly = resetOnly;
            this.nextActiveTab = nextActiveTab;
        }

        static TabCloseResult invalid() {
            return new TabCloseResult(false, false, null);
        }
    }

    private final ReBrowserStore store;
    private final ReBrowserHistory history;
    private final List<ReBrowserStore.Workspace> activeWorkspaces;
    private final List<ReBrowserStore.Workspace> shelvedWorkspaces;
    private final List<ReBrowserStore.Workspace> activeView;
    private final List<ReBrowserStore.Workspace> shelvedView;
    private ReBrowserStore.Workspace activeWorkspace;
    // Runtime-only links: restored metadata has no corresponding live navigation history.
    private final java.util.Map<ReBrowserStore.Tab, ReBrowserStore.Tab> popupSources =
            new java.util.IdentityHashMap<>();

    ReBrowserWorkspaceController(ReBrowserStore store) {
        this(store, null);
    }

    ReBrowserWorkspaceController(ReBrowserStore store, ReBrowserHistory history) {
        this.store = store;
        this.history = history;
        activeWorkspaces = new java.util.ArrayList<>();
        shelvedWorkspaces = new java.util.ArrayList<>();
        activeView = Collections.unmodifiableList(activeWorkspaces);
        shelvedView = Collections.unmodifiableList(shelvedWorkspaces);
    }

    ReBrowserStore.Workspace initialize(String homeUrl) {
        if (!activeWorkspaces.isEmpty() || !shelvedWorkspaces.isEmpty()
                || activeWorkspace != null) return null;
        activeWorkspaces.addAll(store.loadPersistentWorkspaces());
        shelvedWorkspaces.addAll(store.loadShelvedSecondaryWorkspaces(activeWorkspaces));
        ReBrowserStore.Workspace created = null;
        if (activeWorkspaces.isEmpty()) {
            created = newTemporary(homeUrl);
            activeWorkspaces.add(created);
        }
        activeWorkspace = chooseInitialWorkspace();
        if (history != null) history.reconcile(activeWorkspaces);
        return created;
    }

    List<ReBrowserStore.Workspace> activeWorkspaces() {
        return activeView;
    }

    List<ReBrowserStore.Workspace> shelvedWorkspaces() {
        return shelvedView;
    }

    ReBrowserStore.Workspace activeWorkspace() {
        return activeWorkspace;
    }

    int totalWorkspaceCount() {
        return activeWorkspaces.size() + shelvedWorkspaces.size();
    }

    boolean canCreateWorkspace() {
        return totalWorkspaceCount() < ReBrowserStore.MAX_WORKSPACES;
    }

    ReBrowserStore.Workspace createTemporary(String homeUrl) {
        if (!canCreateWorkspace()) return null;
        ReBrowserStore.Workspace workspace = newTemporary(homeUrl);
        activeWorkspaces.add(workspace);
        return workspace;
    }

    ReBrowserStore.Workspace ensureActiveWorkspace(String homeUrl) {
        if (activeWorkspaces.isEmpty()) activeWorkspaces.add(newTemporary(homeUrl));
        if (activeWorkspace == null || !activeWorkspaces.contains(activeWorkspace)) {
            activeWorkspace = chooseInitialWorkspace();
        }
        return activeWorkspace;
    }

    boolean activate(ReBrowserStore.Workspace workspace) {
        if (workspace == null || !activeWorkspaces.contains(workspace)) return false;
        if (workspace != activeWorkspace) clearPopupSources(activeWorkspace);
        activeWorkspace = workspace;
        return true;
    }

    void clearActiveWorkspace() {
        clearPopupSources(activeWorkspace);
        activeWorkspace = null;
    }

    ReBrowserStore.Tab addTab(ReBrowserStore.Workspace workspace, String url) {
        if (!activeWorkspaces.contains(workspace)) return null;
        return store.addTab(workspace, url);
    }

    ReBrowserStore.Tab addPopupTab(
            ReBrowserStore.Workspace workspace,
            ReBrowserStore.Tab source
    ) {
        if (workspace == null || workspace != activeWorkspace || source == null
                || !workspace.tabs.contains(source) || workspace.activeTab() != source) return null;
        ReBrowserStore.Tab tab = addTab(workspace, "about:blank");
        if (tab != null) popupSources.put(tab, source);
        return tab;
    }

    ReBrowserStore.Tab popupSource(
            ReBrowserStore.Workspace workspace,
            ReBrowserStore.Tab tab
    ) {
        if (workspace == null || tab == null || !activeWorkspaces.contains(workspace)
                || !workspace.tabs.contains(tab)) return null;
        ReBrowserStore.Tab source = popupSources.get(tab);
        if (source != null && workspace.tabs.contains(source)) return source;
        popupSources.remove(tab);
        return null;
    }

    void discardAddedTab(ReBrowserStore.Workspace workspace, ReBrowserStore.Tab tab) {
        if (workspace == null || tab == null || !activeWorkspaces.contains(workspace)
                || !workspace.tabs.contains(tab)) return;
        ReBrowserStore.Tab source = popupSource(workspace, tab);
        boolean wasActive = tab.id.equals(workspace.activeTabId);
        workspace.tabs.remove(tab);
        forgetPopupTab(tab);
        if (wasActive && source != null) workspace.activeTabId = source.id;
        workspace.activeTab();
        save();
    }

    boolean selectTab(ReBrowserStore.Workspace workspace, ReBrowserStore.Tab tab) {
        if (workspace == null || tab == null || !activeWorkspaces.contains(workspace)
                || !workspace.tabs.contains(tab)) return false;
        workspace.activeTabId = tab.id;
        return true;
    }

    TabCloseResult closeTab(
            ReBrowserStore.Workspace workspace,
            ReBrowserStore.Tab tab,
            String homeUrl
    ) {
        if (workspace == null || tab == null || !activeWorkspaces.contains(workspace)
                || !workspace.tabs.contains(tab)) return TabCloseResult.invalid();
        if (history != null) history.navigationStarted(tab.id);
        if (workspace.tabs.size() == 1) {
            forgetPopupTab(tab);
            tab.title = "新子标签页";
            tab.url = ReBrowserStore.safeUrl(homeUrl);
            workspace.activeTabId = tab.id;
            save();
            return new TabCloseResult(true, true, tab);
        }
        int oldIndex = workspace.tabs.indexOf(tab);
        boolean wasActive = tab.id.equals(workspace.activeTabId);
        ReBrowserStore.Tab source = popupSource(workspace, tab);
        workspace.tabs.remove(tab);
        forgetPopupTab(tab);
        ReBrowserStore.Tab next = null;
        if (wasActive) {
            int nextIndex = Math.min(oldIndex, workspace.tabs.size() - 1);
            next = source != null ? source : workspace.tabs.get(nextIndex);
            workspace.activeTabId = next.id;
        }
        save();
        return new TabCloseResult(true, false, next);
    }

    void updateTab(ReBrowserStore.Tab tab, String url, String title) {
        if (!ownsTab(tab)) return;
        if (url != null) tab.url = ReBrowserStore.safeUrl(url);
        if (title != null && !title.isBlank()) {
            tab.title = boundedTitle(title);
        }
    }

    void updatePageMetadata(ReBrowserStore.Tab tab, String url, String title) {
        updateTab(tab, url, title);
        ReBrowserStore.Workspace workspace = activeWorkspace;
        if (workspace != null && workspace.level == ReBrowserStore.Level.TEMPORARY
                && tab != null && tab.id.equals(workspace.activeTabId)
                && title != null && !title.isBlank()) {
            workspace.title = boundedTitle(title);
        }
    }

    boolean lockAsSecondary(ReBrowserStore.Workspace workspace) {
        boolean changed = activeWorkspaces.contains(workspace)
                && store.lockAsSecondary(workspace, activeWorkspaces);
        if (changed && history != null) history.workspaceChanged(workspace, true);
        return changed;
    }

    boolean unlockToTemporary(ReBrowserStore.Workspace workspace) {
        boolean changed = activeWorkspaces.contains(workspace)
                && store.unlockToTemporary(workspace, activeWorkspaces);
        if (changed && history != null) history.workspaceChanged(workspace, true);
        return changed;
    }

    boolean promoteToPrimary(ReBrowserStore.Workspace workspace, boolean allowTemporary) {
        boolean changed = activeWorkspaces.contains(workspace)
                && store.promoteToPrimary(workspace, activeWorkspaces, allowTemporary);
        if (changed && history != null) history.workspaceChanged(workspace, true);
        return changed;
    }

    boolean demotePrimaryToSecondary(ReBrowserStore.Workspace workspace) {
        boolean changed = activeWorkspaces.contains(workspace)
                && store.demotePrimaryToSecondary(workspace, activeWorkspaces);
        if (changed && history != null) history.workspaceChanged(workspace, true);
        return changed;
    }

    boolean canPromoteToPrimary() {
        return store.canPromoteToPrimary(activeWorkspaces);
    }

    boolean shelfSecondary(ReBrowserStore.Workspace workspace) {
        if (!activeWorkspaces.contains(workspace)
                || workspace.level != ReBrowserStore.Level.SECONDARY) return false;
        clearPopupSources(workspace);
        store.shelfSecondary(workspace, activeWorkspaces, shelvedWorkspaces);
        if (workspace == activeWorkspace) activeWorkspace = null;
        boolean changed = shelvedWorkspaces.contains(workspace);
        if (changed && history != null) history.workspaceChanged(workspace, false);
        return changed;
    }

    boolean removeTemporary(ReBrowserStore.Workspace workspace) {
        if (!activeWorkspaces.contains(workspace)
                || workspace.level != ReBrowserStore.Level.TEMPORARY) return false;
        clearPopupSources(workspace);
        store.removeWorkspace(workspace, activeWorkspaces);
        if (workspace == activeWorkspace) activeWorkspace = null;
        boolean changed = !activeWorkspaces.contains(workspace);
        if (changed && history != null) history.workspaceChanged(workspace, false);
        return changed;
    }

    boolean restoreSecondary(ReBrowserStore.Workspace workspace) {
        boolean changed = shelvedWorkspaces.contains(workspace)
                && store.restoreSecondary(workspace, activeWorkspaces, shelvedWorkspaces);
        if (changed && history != null) history.workspaceChanged(workspace, true);
        return changed;
    }

    boolean deleteShelvedSecondary(ReBrowserStore.Workspace workspace) {
        if (!shelvedWorkspaces.contains(workspace)) return false;
        store.deleteShelvedSecondary(workspace, shelvedWorkspaces);
        return !shelvedWorkspaces.contains(workspace);
    }

    void save() {
        store.save(activeWorkspaces);
    }

    void saveAll() {
        store.save(activeWorkspaces);
        store.saveShelvedSecondaryWorkspaces(shelvedWorkspaces);
    }

    Set<String> pendingProfileDeletions() {
        return store.pendingProfileDeletions();
    }

    void unmarkProfileForDeletion(String profileName) {
        store.unmarkProfileForDeletion(profileName);
    }

    private void forgetPopupTab(ReBrowserStore.Tab tab) {
        popupSources.remove(tab);
        popupSources.values().removeIf(source -> source == tab);
    }

    private void clearPopupSources(ReBrowserStore.Workspace workspace) {
        if (workspace == null) return;
        for (ReBrowserStore.Tab tab : workspace.tabs) forgetPopupTab(tab);
    }

    private boolean ownsTab(ReBrowserStore.Tab tab) {
        if (tab == null) return false;
        for (ReBrowserStore.Workspace workspace : activeWorkspaces) {
            if (workspace.tabs.contains(tab)) return true;
        }
        for (ReBrowserStore.Workspace workspace : shelvedWorkspaces) {
            if (workspace.tabs.contains(tab)) return true;
        }
        return false;
    }

    private ReBrowserStore.Workspace newTemporary(String homeUrl) {
        ReBrowserStore.Workspace workspace = store.createTemporaryWorkspace();
        ReBrowserStore.Tab tab = workspace.activeTab();
        if (tab != null) tab.url = ReBrowserStore.safeUrl(homeUrl);
        return workspace;
    }

    private ReBrowserStore.Workspace chooseInitialWorkspace() {
        for (ReBrowserStore.Workspace workspace : activeWorkspaces) {
            if (workspace.level == ReBrowserStore.Level.PRIMARY) return workspace;
        }
        return activeWorkspaces.get(0);
    }

    private static String boundedTitle(String value) {
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.substring(0, Math.min(clean.length(), 160));
    }
}
