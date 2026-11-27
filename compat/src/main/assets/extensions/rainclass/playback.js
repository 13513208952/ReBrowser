rainClassActivation.then(active => {
  if (!active) return;

  const videoSelector = '.xtplayer video.xt_video_player';
  const playControl = 'video.xt_video_player, .xt_video_bit_play_btn, ' +
    '.xt_video_player_play_btn, .xt_video_poster, .xt_video_player_big_play_layer';
  const directorySelector = '.learning-space-student-chapter-nav .nav-list';
  const rowSelector = '.leaf-item';
  const closedChapter = '.nav-item-title:not(.is-expand):has(.expand-icon)';

  let intent;
  let run;
  let frame = 0;
  const observer = new MutationObserver(schedule);

  function foreground() {
    return document.visibilityState === 'visible' && document.hasFocus();
  }

  function videoPath() {
    const path = location.pathname;
    return /^\/ai-workspace\/lms-graph\/\d+\/video\/\d+\/?$/.test(path) ? path : null;
  }

  function unitStatus(row) {
    const header = document.querySelector('.learning-space-control-unit');
    if (row && header?.querySelector('.unit-title')?.textContent.trim() !==
      row.querySelector('.leaf-item-title')?.textContent.trim()) return null;

    const label = header?.querySelector('.rate-detail .text')?.textContent.trim();
    if (/^(已完成|completed)$/i.test(label)) return 'complete';
    if (/^(完成度|completion(?: rate)?|progress)\s*[：:]\s*\d+(?:\.\d+)?\s*%$/i.test(label)) {
      return 'incomplete';
    }
    return null;
  }

  function selectDoubleSpeed(video) {
    if (video.playbackRate === 2) return true;
    const option = video.closest('.xtplayer')?.querySelector('.xt_video_player_speed li[data-speed="2"]');
    if (!option) return false;

    // The desktop speed menu accepts clicks only after pointer movement.
    option.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, clientX: 10 }));
    option.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: 11 }));
    option.click();
    option.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, clientX: 11 }));
    return video.playbackRate === 2;
  }

  function begin(video, path) {
    stopObserving();
    video.muted = true;
    run = { phase: 'playing', video, path, speedPending: !selectDoubleSpeed(video) };
    return run;
  }

  function stopObserving() {
    observer.disconnect();
    if (frame) cancelAnimationFrame(frame);
    frame = 0;
  }

  function stop() {
    intent = null;
    run = null;
    stopObserving();
  }

  function schedule() {
    if (frame || !run || run.phase === 'playing' || !foreground()) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      if (foreground()) advance();
    });
  }

  function waitForPage() {
    observer.observe(document.body, {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['class'],
    });
    schedule();
  }


  function advance() {
    if (!run) return;
    const path = videoPath();
    const nav = document.querySelector(directorySelector);
    const current = nav?.querySelector(`${rowSelector}.is-active`);
    if (run.phase === 'opening') {
      if (!path) return stop();
      if (path === run.path || !run.target.matches('.is-active')) return;
      const video = document.querySelector(videoSelector);
      if (!video || video === run.video) return;
      const status = unitStatus(run.target);
      if (status === 'complete') {
        run = { phase: 'finished', video, path };
        schedule();
      } else if (status === 'incomplete') {
        if (run.mutedVideo !== video) {
          video.muted = true;
          run.mutedVideo = video;
        }
        if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return;
        const started = begin(video, path);
        if (video.paused) {
          video.play().catch(() => {
            // Leave the player available for a manual start if Firefox blocks autoplay.
            if (run === started && video.paused) stop();
          });
        }
      }
      return;
    }

    if (path !== run.path) return stop();
    if (!nav || unitStatus(current) !== 'complete') return;
    if (run.expanding?.isConnected && !run.expanding.classList.contains('is-expand')) return;

    const target = nextDirectoryEntry(nav, /^(视频|video)$/i);
    if (!target) return stop();
    if (target.matches(closedChapter)) {
      run.expanding = target;
      target.click();
    } else {
      run = { phase: 'opening', video: run.video, path, target };
      target.click();
    }
  }

  function manualIntent(event) {
    if (!event.isTrusted || !(event.target instanceof Element)) return;
    if (event.type === 'click' && event.target.closest(`${directorySelector} ${rowSelector}`)) {
      stop();
      return;
    }
    if (!foreground() || !event.target.closest(playControl)) return;
    if (event.type === 'keydown' && (![' ', 'Enter'].includes(event.key) || event.repeat)) return;
    if (event.type !== 'keydown' && event.button !== 0) return;
    const path = videoPath();
    const video = event.target.closest('.xtplayer')?.querySelector('video.xt_video_player');
    if (path && video?.paused) intent = { video, path, expires: performance.now() + 1500 };
  }

  function mediaChanged(event) {
    const video = event.target;
    if (!event.isTrusted || !video.matches?.(videoSelector)) return;
    const path = videoPath();
    if (!path) return;

    if (event.type === 'ended') {
      if (run?.phase === 'playing' && run.video === video && run.path === path && video.ended) {
        run.phase = 'finished';
        waitForPage();
      }
      return;
    }

    if (!foreground()) return;
    if (event.type === 'play' && intent?.video === video && intent.path === path &&
      performance.now() <= intent.expires) {
      intent = null;
      if (run?.phase === 'playing' && run.video === video && run.path === path) return;
      const active = document.querySelector(`${directorySelector} ${rowSelector}.is-active`);
      if (unitStatus(active) === 'incomplete') begin(video, path);
      return;
    }

    if (run?.phase === 'playing' && run.video === video && run.path === path && run.speedPending) {
      run.speedPending = !selectDoubleSpeed(video);
    } else {
      schedule();
    }
  }

  for (const type of ['pointerdown', 'click', 'keydown']) {
    document.addEventListener(type, manualIntent, true);
  }
  for (const type of ['play', 'loadedmetadata', 'canplay', 'ended']) {
    document.addEventListener(type, mediaChanged, true);
  }
  document.addEventListener('visibilitychange', () => {
    if (!foreground()) intent = null;
    schedule();
  });
  window.addEventListener('blur', () => { intent = null; });
  window.addEventListener('focus', schedule);
});
