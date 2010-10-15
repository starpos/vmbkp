.PHONY: all build test clean rebuild doc

all: build doc test

build: 
	$(MAKE) -C src
	$(MAKE) -C test

test:
	$(MAKE) -C test test

clean:
	$(MAKE) -C src clean
	$(MAKE) -C test clean

doc:
	doxygen doxygen.config

gtags_doc:
	gtags -v
	#htags -anosT --tabs 4

rebuild: clean build doc

