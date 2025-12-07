mainJava = $(wildcard app/src/main/java/org/example/*.java)
postfixFile = app/sample/postfix/main.postfix

all: runChain

# == JVM
runChain: target = "sample/scratch.ms2"
runChain:
	gradle runChain -Ptarget=$(target) --console=plain

jrec: target = "sample/test_translator/1_recursion.ms2"
jrec:
	gradle runChain -Ptarget=$(target) --console=plain

jflow: target = "sample/test_translator/2_flow_control.ms2"
jflow:
	gradle runChain -Ptarget=$(target) --console=plain

jarith: target = "sample/test_translator/3_complex_arith.ms2"
jarith:
	gradle runChain -Ptarget=$(target) --console=plain

jswitch: target = "sample/test_translator/4_switch_case.ms2"
jswitch:
	gradle runChain -Ptarget=$(target) --console=plain

jconcatAndScope: target = "sample/test_translator/5_concat_and_scope.ms2"
jconcatAndScope:
	gradle runChain -Ptarget=$(target) --console=plain

jforStmt: target = "sample/test_translator/6_for_loop.ms2"
jforStmt:
	gradle runChain -Ptarget=$(target) --console=plain

jstrings: target = "sample/test_translator/7_string_funcs.ms2"
jstrings:
	gradle runChain -Ptarget=$(target) --console=plain

jvoid: target = "sample/test_translator/8_void.ms2"
jvoid:
	gradle runChain -Ptarget=$(target) --console=plain

jparse: target = "sample/test_translator/9_parse_string.ms2"
jparse:
	gradle runChain -Ptarget=$(target) --console=plain

jsimple: target = "sample/simple.ms2"
jsimple:
	gradle runChain -Ptarget=$(target) --console=plain

jbasic: target = "sample/basic.ms2"
jbasic:
	gradle runChain -Ptarget=$(target) --console=plain

jAllTest: jrec jflow jarith jswitch jconcatAndScope jforStmt jstrings jvoid jparse

# == PSM
runPSM: $(postfixFile)
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

strings: $(mainJava)
	./gradlew run --args="sample/test_translator/7_string_funcs.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

void: $(mainJava)
	./gradlew run --args="sample/test_translator/8_void.ms2"
	@echo "============="
	python3 psm/PSM.py -p app/sample/postfix -m main

parse: $(mainJava)
	./gradlew run --args="sample/test_translator/9_parse_string.ms2"
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

allTest: rec flow arith switch concatAndScope forStmt strings void parse
