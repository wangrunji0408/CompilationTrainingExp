
# if java/javac are not in your ${PATH}, set this line to the directory
# they are located in, with a trailing '/', e.g. 'JAVAPATH=/usr/bin/'
JAVAPATH=/opt/jdk1.5.0_22/bin/

compile: clean
	chmod +x run.sh
	mkdir build
	${JAVAPATH}javac -Xlint:-options -source 1.5 -target 1.5 \
	  -cp lib/joeq.jar \
	  -sourcepath src -d build `find src -name "*.java"`

clean:
	rm -rf build/
