globalThis.ds = Object.freeze({
  model: 'deepseek-flash',
  endpoint: 'https://api.deepseek.com/chat/completions',
  exerciseFrame: 'iframe#iframeExerciseId',
  maxImages: 24,
  maxText: 30000,
  maxAttachments: 4,
  maxImageBytes: 4 * 1024 * 1024,
  maxPayload: 40 * 1024 * 1024,
  efforts: ['max', 'high', 'low', 'none'],
  quickPrompt: '请问这个该怎么写，请简要回答',
  unit(url) {
    const { origin, pathname } = new URL(url);
    if (origin === 'https://examination.xuetangx.com' && /^\/(?:exam|result)\/\d+\/?$/.test(pathname)) return 'exam';
    if (origin !== 'https://www.yuketang.cn') return null;
    const graph = pathname.match(/^\/ai-workspace\/lms-graph\/\d+\/(graph|forum|exercise|quiz|self-test)\/\d+\/?$/);
    if (graph) return graph[1];
    return /^\/v2\/web\/(?:exam|iframe-quiz|studentQuiz_web|quizResult)\/\d+\/\d+\/?$/.test(pathname)
      ? 'exam' : null;
  },
});
