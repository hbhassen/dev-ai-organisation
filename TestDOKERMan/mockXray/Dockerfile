FROM python@sha256:e6dae589c1c05976188fd3bd019afd091445fc533ed2ea29001beddc7368a9c8
WORKDIR /app
COPY app.py .
USER 65534:65534
EXPOSE 8080
CMD ["python", "-u", "app.py"]
