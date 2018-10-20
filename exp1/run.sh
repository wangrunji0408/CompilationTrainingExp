#!/bin/sh
here=`dirname $0`
/opt/jdk1.5.0_22/bin/java -cp build/:${here}/lib/joeq.jar $@
