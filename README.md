# NIOServer - non-blocking I/O server demo in Kotlin

A single threaded server using non-blocking I/O that can receive UTF-8-encoded
string messages. Implemented using java.nio in Kotlin and serves as demo/example
code.

## Stream protocol for message reception

Variable length UTF-8 encoded messages, up to a max receive buffer size each,
with end message marker byte (<code>0x00</code>). Server does not explicitly
acknowledge messages at application layer.

## Getting server side messages in code

The <code>NIOServer</code> instance provides received messages through a
concurrent bounded queue.

## Building and running tests

    mvn install
    
## Running server example

Fire up server on localhost, port 9999:

    ./run-server.sh 9999
    
(This script runs the main function of class <code>MainKt</code> by executing jar.)

In some other terminal, send the server messages consisting of the file names in
your home directory:

    ls -1 $HOME|tr \\n \\0|nc -w1 localhost 9999
    
(Each message must be ended by a null byte.)

Now check server log.

## Motivation

It was just a fun exercise mainly to learn a bit about java.nio non-blocking
I/O, the <code>ByteBuffer</code> and the <code>Selector</code> API.
