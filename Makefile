# Generate outputs/MiniRocket.v, outputs/LinuxRocket.v and
# outputs/cflush/LinuxRocket.v

JAVA_OPTS ?= -Xmx16G -Xss8M
SBT       := java $(JAVA_OPTS) -jar rocket-chip/sbt-launch.jar
OUTPUTS   := outputs
SOURCES   := build.sbt $(wildcard src/main/scala/*.scala) bootrom/bootrom.img

all: $(OUTPUTS)/MiniRocket.v $(OUTPUTS)/LinuxRocket.v $(OUTPUTS)/cflush/LinuxRocket.v

$(OUTPUTS)/MiniRocket.v: $(SOURCES)
	$(SBT) "runMain cpuss.Main -td $(OUTPUTS)"

$(OUTPUTS)/LinuxRocket.v: $(SOURCES)
	$(SBT) "runMain cpuss.LinuxMain -td $(OUTPUTS)"

# LinuxRocket with a blocking data cache and cflush.d.l1, for checkpoint tests
$(OUTPUTS)/cflush/LinuxRocket.v: $(SOURCES)
	$(SBT) "runMain cpuss.LinuxCFlushMain -td $(OUTPUTS)/cflush"

clean:
	rm -rf $(OUTPUTS)

.PHONY: all clean
