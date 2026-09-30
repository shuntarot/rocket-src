# Generate outputs/HNSCore.v

JAVA_OPTS ?= -Xmx16G -Xss8M
SBT       := java $(JAVA_OPTS) -jar rocket-chip/sbt-launch.jar
OUTPUTS   := outputs

all: $(OUTPUTS)/HNSCore.v

$(OUTPUTS)/HNSCore.v: build.sbt $(wildcard src/main/scala/*.scala) bootrom/bootrom.img
	$(SBT) "run -td $(OUTPUTS)"

clean:
	rm -rf $(OUTPUTS)

.PHONY: all clean
