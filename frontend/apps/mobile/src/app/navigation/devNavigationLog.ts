
/**
 * Log điều hướng chỉ ở bản dev (không gửi đi đâu, chỉ in ra Metro/logcat): màn đầu khi mở app và mỗi
 * lần đổi màn, kèm đường dẫn route lồng nhau (vd. Onboarding/PlacementIntro). Dùng để tìm lỗi mở lại app
 * vào sai màn. Không in params (có thể chứa id/email).
 */
type NavState = { index?: number; routes: { name: string; state?: unknown }[] } | undefined;

export function createDevNavigationLog() {
  let last = '';
  const path = (root: unknown) => {
    const names: string[] = [];
    let state = root as NavState;
    while (state && state.routes.length > 0) {
      const route = state.routes[state.index ?? state.routes.length - 1];
      if (!route) break;
      names.push(route.name);
      state = route.state as typeof state;
    }
    return names.join('/');
  };
  const log = (event: 'ready' | 'change', root: unknown) => {
    if (!__DEV__) return;
    const current = path(root);
    if (event === 'change' && current === last) return;
    last = current;
    console.log(`[nav] ${event === 'ready' ? 'start' : 'go'} ${current}`);
  };
  return { onReady: (root: unknown) => log('ready', root), onStateChange: (root: unknown) => log('change', root) };
}
