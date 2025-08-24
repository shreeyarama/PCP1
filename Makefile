# PCP1 Parallel Makefile
# Usage:
#   make all
#   make run ARGS="100 0.2 20"
#   make clean

JAVAC := javac
JAVA  := java

SOURCES := DungeonMapParallel.java HuntParallel.java DungeonHunterParallel.java
MAIN    := DungeonHunterParallel
ARGS ?= 100 0.2 0

all: build

build: $(SOURCES)
	$(JAVAC) $(SOURCES)

run: build
	$(JAVA) $(MAIN) $(ARGS)

clean:
	rm -f *.class *.png