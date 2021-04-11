## Simple TCP
simple model of TCP-over-UDP with: 
 * ack/seq numbers per packets 
 * some handshakes
 * timers
 * potentially full-duplex (not on this testbed)
 * packets numbers instead of TCP's bytestream
 * funky one-byte flags because bit flags are not the goal
 * and brightest threads ideas /s

TODO: lost ack in FIN handshake can leave timer working, 
need to figure out

