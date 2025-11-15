mainJava = $(wildcard app/src/main/java/org/example/*.java)
postfixFile = app/sample/postfix/main.postfix

all: run

run: $(postfixFile)
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

$(postfixFile): $(mainJava) app/sample/scratch.ms2
	./gradlew run --args="sample/scratch.ms2"

# == tests
rec: $(mainJava)
	./gradlew run --args="sample/test_translator/1_recursion.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

flow: $(mainJava)
	./gradlew run --args="sample/test_translator/2_flow_control.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

arith: $(mainJava)
	./gradlew run --args="sample/test_translator/3_complex_arith.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

switch: $(mainJava)
	./gradlew run --args="sample/test_translator/4_switch_case.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

concatAndScope: $(mainJava)
	./gradlew run --args="sample/test_translator/5_concat_and_scope.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

forStmt: $(mainJava)
	./gradlew run --args="sample/test_translator/6_for_loop.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

basic: $(mainJava)
	./gradlew run --args="sample/basic.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

simple: $(mainJava)
	./gradlew run --args="sample/simple.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

allTest: rec flow arith switch concatAndScope forStmt
