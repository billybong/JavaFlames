# Flamegraph from JFR logs
Simple java script to generate flamegraphs from Java flight recordings without requiring to install Perl and the Brendan Gregg scripts.

## Usage
With a fairly recent Java version install simply run:
`java JavaFlames.java <path to jfr recording file>`

This launches an http server serving the `flamegraph.html` file and endpoint for the folded format extracted from the JFR logs.
After that the script opens up a browser showing the results.

## Credits
Brendan Gregg for introducing the concept of flamegraphs and https://github.com/spiermar/d3-flame-graph for the javascript/html implementation.