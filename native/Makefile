# GS AI — C++ native engine (Phase F). Plain Makefile: the sandbox has g++ but
# no cmake. Device builds (Android NDK / Xcode) use the same sources with
# CMake/podspec wiring documented in native/README.md.

CXX      ?= g++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra -fno-rtti -pthread
INCLUDES  = -Iinclude
BUILD     = build

SRCS = src/plugin_registry.cpp src/agent_loop.cpp \
       src/backends/mock_backend.cpp src/backends/cactus_backend.cpp \
       src/backends/llamacpp_backend.cpp
OBJS = $(patsubst src/%.cpp,$(BUILD)/%.o,$(SRCS))

.PHONY: all smoke clean

all: smoke

$(BUILD):
	mkdir -p $(BUILD)/backends

$(BUILD)/%.o: src/%.cpp | $(BUILD)
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

$(BUILD)/libgs_engine.a: $(OBJS)
	ar rcs $@ $(OBJS)

$(BUILD)/smoke: tests/smoke.cpp $(BUILD)/libgs_engine.a
	$(CXX) $(CXXFLAGS) $(INCLUDES) tests/smoke.cpp $(BUILD)/libgs_engine.a -ldl -o $@

smoke: $(BUILD)/smoke
	./$(BUILD)/smoke

clean:
	rm -rf $(BUILD)
