var fs = require('fs');

var server;
if (process.env.SSL) {
  server = require('https').createServer({
    key: fs.readFileSync(__dirname + '/key.pem'),
    cert: fs.readFileSync(__dirname + '/cert.pem')
  });
} else {
  server = require('http').createServer();
}

var io = require('socket.io')(server, {
  pingInterval: 2000,
  wsEngine: 'ws'
});
var port = process.env.PORT || 3000;
var nsp = process.argv[2] || '/';
var slice = Array.prototype.slice;

const fooNsp = io.of('/foo');

fooNsp.on('connection', (socket) => {
  socket.on('room', (...args) => {
    fooNsp.to(socket.id).emit.apply(fooNsp, ['roomBack'].concat(args));
  });
});

io.of('/timeout_socket').on('connection', function() {
  // register namespace
});

io.of('/valid').on('connection', function() {
  // register namespace
});

io.of('/asd').on('connection', function() {
  // register namespace
});

io.of('/abc').on('connection', function(socket) {
  socket.emit('handshake', socket.handshake);
});

io.of(nsp).on('connection', function(socket) {
  socket.send('hello client');

  socket.on('message', function() {
    var args = slice.call(arguments);
    socket.send.apply(socket, args);
  });

  socket.on('echo', function() {
    var args = slice.call(arguments);
    socket.emit.apply(socket, ['echoBack'].concat(args));
  });

  socket.on('ack', function() {
    var args = slice.call(arguments);
    var callback = args.pop();
    callback.apply(null, args);
  });

  socket.on('callAck', function() {
    socket.emit('ack', function() {
      var args = slice.call(arguments);
      socket.emit.apply(socket, ['ackBack'].concat(args));
    });
  });

  socket.on('callAckBinary', function() {
    socket.emit('ack', function(buf) {
      socket.emit('ackBack', buf);
    });
  });

  socket.on('getAckBinary', function(data, callback) {
    var buf = new Buffer('huehue', 'utf8');
    callback(buf);
  });

  socket.on('getAckDate', function(data, callback) {
    callback(new Date());
  });

  socket.on('broadcast', function(data) {
    var args = slice.call(arguments);
    socket.broadcast.emit.apply(socket, ['broadcastBack'].concat(args));
  });

  socket.on('room', (...args) => {
    io.to(socket.id).emit.apply(io.sockets, ['roomBack'].concat(args));
  });

  socket.on('requestDisconnect', function() {
    socket.disconnect();
  });

  socket.on('disconnect', function() {
    console.log('disconnect');
  });

  socket.on('error', function() {
    console.log('error: ', arguments);
  });

  socket.on('getHandshake', function(cb) {
    cb(socket.handshake);
  });
});


function before(context, name, fn) {
  var method = context[name];
  context[name] = function() {
    fn.apply(this, arguments);
    return method.apply(this, arguments);
  };
}

before(io.engine, 'handleRequest', function(req, res) {
  // echo a header value
  var value = req.headers['x-socketio'];
  if (!value) return;
  res.setHeader('X-SocketIO', value);
});

before(io.engine, 'handleUpgrade', function(req, socket, head) {
  // echo a header value for websocket handshake
  var value = req.headers['x-socketio'];
  if (!value) return;
  this.ws.once('headers', function(headers) {
    headers.push('X-SocketIO: ' + value);
  });
});


server.listen(port, function() {
  console.log('Socket.IO server listening on port', port);
});
