CXX ?= c++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra -pedantic
JAVAC ?= javac
JAVA ?= java

.PHONY: all run test clean
all: build/levelgen build/classes/MazeGame.class

build/levelgen: cpp/levelgen.cpp
	mkdir -p build
	$(CXX) $(CXXFLAGS) $< -o $@

build/classes/MazeGame.class: java/MazeGame.java
	mkdir -p build/classes
	$(JAVAC) -encoding UTF-8 -d build/classes $<

run: all
	$(JAVA) -cp build/classes MazeGame maps/default.map

test: all
	$(JAVAC) -encoding UTF-8 -cp build/classes -d build/classes tests/GameTest.java
	$(JAVA) -Djava.awt.headless=true -cp build/classes GameTest maps/default.map
	JAVA=$(JAVA) python3 tests/test_project.py

clean:
	rm -rf build
