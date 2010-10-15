test: config.sh
	./expr.sh
	./check.sh > check.result
	diff -u check.correct check.correct > result
	if [ -s result ]; then echo "Test failed."; else echo "Test succeeded."; fi

bindir:
	ln -s ../src ./bin
