package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Looper;
import io.github.rebrowser.browser.BrowserModels.*;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Per-window presentation only; selected refs are NOT domain ownership/permission/close authority. */
public final class OverviewSelection {
    public enum Panel { NONE, WORKSPACES, TABS }
    /** Actual selection reducer shared by UI and HOST tests; no Activity or data/IPC access. */
    public static final class State {
        private final Thread owner=Thread.currentThread();private final Set<WorkspaceRef> workspaces=new LinkedHashSet<>();private final Set<TabRef> tabs=new LinkedHashSet<>();private final Set<WorkspaceRef> knownWorkspaces=new LinkedHashSet<>();private final Set<TabRef> knownTabs=new LinkedHashSet<>();
        private Panel panel=Panel.NONE;private WorkspaceRef tabWorkspace;private long revision=1;private boolean retired;
        public void show(Panel next,WorkspaceRef workspace){check();if(next==null || (next==Panel.TABS && workspace==null) || (next!=Panel.TABS && workspace!=null))throw invalid();if(panel!=next || tabWorkspace!=workspace){advance();workspaces.clear();tabs.clear();knownWorkspaces.clear();knownTabs.clear();panel=next;tabWorkspace=workspace;}}
        public boolean toggleWorkspace(WorkspaceRef ref,long expectedRevision){current(expectedRevision);if(panel!=Panel.WORKSPACES || ref==null || !knownWorkspaces.contains(ref))throw invalid();return toggle(workspaces,ref,64);}
        public boolean toggleTab(TabRef ref,long expectedRevision){current(expectedRevision);if(panel!=Panel.TABS || ref==null || ref.workspace()!=tabWorkspace || !knownTabs.contains(ref))throw invalid();return toggle(tabs,ref,50);}
        private <T> boolean toggle(Set<T> values,T ref,int capacity){if(values.contains(ref)){advance();values.remove(ref);return false;}if(values.size()>=capacity)throw new IllegalStateException("Native selection limit reached");advance();values.add(ref);return true;}
        public void reconcile(WorkspacesView view,TabsView group){
            check();if(view==null)throw invalid();Set<WorkspaceRef> known=new LinkedHashSet<>();for(WorkspaceView w:view.active())known.add(w.ref());for(WorkspaceView w:view.shelved())known.add(w.ref());
            if(known.size()>64)throw invalid();knownWorkspaces.clear();knownWorkspaces.addAll(known);knownTabs.clear();Set<WorkspaceRef> kept=new LinkedHashSet<>(workspaces);kept.retainAll(known);Set<TabRef> keptTabs=new LinkedHashSet<>(tabs);
            if(panel==Panel.TABS){if(group==null || group.workspace()!=tabWorkspace || !known.contains(tabWorkspace)){show(Panel.NONE,null);return;}Set<TabRef> present=new LinkedHashSet<>();for(TabView tab:group.tabs())present.add(tab.ref());if(present.size()>50)throw invalid();knownTabs.addAll(present);keptTabs.retainAll(present);}
            if(!kept.equals(workspaces) || !keptTabs.equals(tabs)){advance();workspaces.clear();workspaces.addAll(kept);tabs.clear();tabs.addAll(keptTabs);}
        }
        /** One Back consumes selection first, never also closes panel/workspace. */
        public boolean clearSelected(){check();if(workspaces.isEmpty() && tabs.isEmpty())return false;advance();workspaces.clear();tabs.clear();return true;}
        public void retire(){thread();if(retired)return;retired=true;if(revision<Long.MAX_VALUE)revision++;workspaces.clear();tabs.clear();knownWorkspaces.clear();knownTabs.clear();panel=Panel.NONE;tabWorkspace=null;}
        public Set<WorkspaceRef> selectedWorkspaces(){check();return Collections.unmodifiableSet(new LinkedHashSet<>(workspaces));}
        public Set<TabRef> selectedTabs(){check();return Collections.unmodifiableSet(new LinkedHashSet<>(tabs));}
        public Panel panel(){check();return panel;}
        public long revision(){check();return revision;}
        private void current(long expected){check();if(expected!=revision)throw new IllegalStateException("Stale native selection revision");}
        private void advance(){if(revision==Long.MAX_VALUE)throw new IllegalStateException("Native selection revision exhausted");revision++;}
        private void check(){thread();if(retired)throw new IllegalStateException("Native selection window retired");}
        private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Selection owner thread required");}
    }
    private final WeakReference<Activity> window;private final State state=new State();
    public OverviewSelection(Activity ownedWindow){if(ownedWindow==null || Looper.myLooper()!=Looper.getMainLooper() || ownedWindow.isDestroyed())throw invalid();window=new WeakReference<>(ownedWindow);}
    public State currentPresentation(){Activity a=window.get();if(a==null || a.isDestroyed() || a.isFinishing())throw new IllegalStateException("Selection window unavailable");return state;}
    public void retire(){state.retire();window.clear();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native overview selection");}
}
