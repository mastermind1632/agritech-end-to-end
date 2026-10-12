FROM certbot/certbot:v5.8.0
RUN pip install --no-cache-dir docker==7.1.0
COPY acme_hook.py /hooks/acme_hook.py
