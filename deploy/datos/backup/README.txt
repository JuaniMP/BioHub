Programar (en biohub-datos), opcion A - systemd timer:
  sudo cp ~/biohub-datos/backup/biohub-backup.{service,timer} /etc/systemd/system/
  sudo mkdir -p /var/backups/biohub && sudo chown biohub: /var/backups/biohub
  sudo systemctl daemon-reload && sudo systemctl enable --now biohub-backup.timer
Opcion B - cron (crontab -e del usuario biohub):
  30 2 * * * /home/biohub/biohub-datos/backup/backup.sh >> /home/biohub/backup-biohub.log 2>&1
Restaurar una instancia:
  gunzip -c ARCHIVO.sql.gz | docker exec -i biohub-mysql-<inst> sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h127.0.0.1'
Recomendado: copiar tambien /var/backups/biohub fuera de la VM (otro equipo).
