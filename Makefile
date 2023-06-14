Alpenblumen.jar: Alpenblumen.class manifest.mf
	jar cmf manifest.mf Alpenblumen.jar *.class *.java

Alpenblumen.class: *.java
	javac *.java

clean:
	rm *.class *.jar
