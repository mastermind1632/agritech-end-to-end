FROM --platform=$BUILDPLATFORM ubuntu:24.04 AS download
ARG TARGETARCH
ARG OLLAMA_VERSION=0.34.4
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl zstd \
    && rm -rf /var/lib/apt/lists/*
RUN case "$TARGETARCH" in \
      amd64) checksum=c238986e61d40c0cc5f4a9b9e40b9eea104350b77efa34741fc134e105cb9533 ;; \
      arm64) checksum=96f50a1192133028cf4e010d8c333f8af14b1505db6be7b2034c11487e7fd7e6 ;; \
      *) exit 1 ;; \
    esac \
    && curl --fail --location --retry 3 "https://github.com/ollama/ollama/releases/download/v${OLLAMA_VERSION}/ollama-linux-${TARGETARCH}.tar.zst" -o /tmp/ollama.tar.zst \
    && echo "$checksum  /tmp/ollama.tar.zst" | sha256sum --check - \
    && mkdir /runtime \
    && tar --zstd -xf /tmp/ollama.tar.zst -C /runtime \
       --exclude='*/cuda*' --exclude='*/rocm*' --exclude='*/vulkan*' \
       --exclude='*/mlx*' --exclude='*/include' \
    && rm /tmp/ollama.tar.zst

FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates libstdc++6 libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=download /runtime/bin/ollama /bin/ollama
COPY --from=download /runtime/lib/ollama /usr/lib/ollama
ENV OLLAMA_HOST=0.0.0.0:11434 LD_LIBRARY_PATH=/usr/lib/ollama
EXPOSE 11434
ENTRYPOINT ["/bin/ollama"]
CMD ["serve"]
