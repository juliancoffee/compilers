mainJava = app/src/main/java/org/example/Translator.java
postfixFile = app/sample/postfix/main.postfix
sampleFile = app/sample/scratch.ms2

all: $(postfixFile)
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

$(postfixFile): $(mainJava) $(sampleFile)
	./gradlew run --args="sample/scratch.ms2"
