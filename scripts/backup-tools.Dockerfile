FROM docker:27.5.1-cli@sha256:851f91d241214e7c6db86513b270d58776379aacc5eb9c4a87e5b47115e3065c AS cli
FROM python:3.13.16-slim-bookworm@sha256:a1165e272e578941b84abc79e4ab38a0305cd12803a5c4247979ac7655f4d641
COPY --from=cli /usr/local/bin/docker /usr/local/bin/docker
COPY scripts/backup_engine.py /opt/backup_engine.py
COPY database/configure-access.sql /opt/configure-access.sql
ENTRYPOINT ["python", "/opt/backup_engine.py"]
