/**
 * Listens for the app launching then creates the window
 *
 * @see http://developer.chrome.com/apps/app.window.html
 */
chrome.app.runtime.onLaunched.addListener(function() {
  chrome.app.window.create('index.html', {
    frame: "none",
    id: 'embedder',
    bounds: {
      width: 870,
      height: 700
    },
    resizable: false
  });
});
