if (window.console) {
  console.log("Welcome to your Play application's JavaScript!");
}

const webSocket = new WebSocket("ws://localhost:9000/ws")

webSocket.onmessage = function (event) {
  console.log(event.data);
}

webSocket.onopen = function (event) {
  webSocket.send("Here's some text that the server is urgently awaiting!");
};

