# Flamegraph from JFR logs
Simple one file Java script to generate flamegraphs from Java flight recordings without installing Perl and the [Brendan Gregg scripts](https://github.com/brendangregg/FlameGraph).

It also comes with a separate script to start profiling a java process using FlightRecorder and then call the flamegraph script. 
## Usage
With a fairly recent Java version installed simply run:
`java JavaFlames.java <path to jfr recording file>`

This launches an http server serving the `index.html` file and endpoint for the folded format extracted from the JFR logs.
The script then opens up a browser showing the results.

Alternatively if you don't have an existing JFR recording you can also start the `Profile.java` script to start profiling a java process generating the JFR file and then triggering flamegraph generation.

`java Profile.java <pid> [<duration in seconds>]`

The default profiling duration unless specified is 30 seconds.

## Credits
Brendan Gregg for introducing the concept of flamegraphs and https://github.com/spiermar/d3-flame-graph for the javascript/html implementation.
