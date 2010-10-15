PREFIX=

default: build
build: build_vmbkp build_vmdkbkp

build_vmbkp:
	$(MAKE) -C vmbkp all
build_vmdkbkp:
	$(MAKE) -C vmdkbkp

install:
	-@ if [ -z "$(PREFIX)" ]; then echo "Specify PREFIX"; \
	else ./install.sh $(PREFIX); fi
