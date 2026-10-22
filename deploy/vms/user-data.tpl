#cloud-config
# Plantilla común para biohub-backend y biohub-datos. crear-vms.sh reemplaza
# __HOSTNAME__ y __SSH_PUBKEY__ (llave pública, no es secreto).
hostname: __HOSTNAME__
manage_etc_hosts: true
timezone: America/Bogota
ssh_pwauth: false
disable_root: true

users:
  - name: biohub
    gecos: BioHub
    groups: [sudo]
    shell: /bin/bash
    sudo: ALL=(ALL) NOPASSWD:ALL
    lock_passwd: true
    ssh_authorized_keys:
      - __SSH_PUBKEY__

package_update: true
package_upgrade: true
packages:
  - ca-certificates
  - curl
  - gnupg
  - ufw
  - unattended-upgrades
  - qemu-guest-agent
  - rsync

write_files:
  - path: /etc/apt/apt.conf.d/20auto-upgrades
    content: |
      APT::Periodic::Update-Package-Lists "1";
      APT::Periodic::Unattended-Upgrade "1";
      APT::Periodic::AutocleanInterval "7";
  # userland-proxy: en biohub-datos es false (sin docker-proxy se conserva la IP de origen real y MySQL
  # ve la IP de Tailscale de backend: usuarios app@<TS_IP_BACKEND>). En biohub-backend queda true (valor por
  # defecto de Docker; probado así). Ojo (SEC-D-10): con docker-proxy el gateway puede ver orígenes 172.x, así que
  # un rate limiting por IP debe revisarse antes de implementarse.
  - path: /etc/docker/daemon.json
    content: |
      {"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"},"userland-proxy":__USERLAND_PROXY__}

runcmd:
  - [bash, -euxc, "install -m 0755 -d /etc/apt/keyrings"]
  # Docker Engine + compose plugin (repo oficial)
  - [bash, -euxc, "curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && chmod a+r /etc/apt/keyrings/docker.asc"]
  - [bash, -euxc, "echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable\" > /etc/apt/sources.list.d/docker.list"]
  # Tailscale (repo oficial)
  - [bash, -euxc, "curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/noble.noarmor.gpg -o /usr/share/keyrings/tailscale-archive-keyring.gpg"]
  - [bash, -euxc, "curl -fsSL https://pkgs.tailscale.com/stable/ubuntu/noble.tailscale-keyring.list -o /etc/apt/sources.list.d/tailscale.list"]
  - [bash, -euxc, "apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin tailscale"]
  - [bash, -euxc, "usermod -aG docker biohub"]
  - [bash, -euxc, "systemctl enable --now docker qemu-guest-agent tailscaled unattended-upgrades"]
  # Firewall: nada entrante salvo SSH desde la red NAT del anfitrión y todo por tailscale0
  - [bash, -euxc, "ufw default deny incoming && ufw default allow outgoing"]
  - [bash, -euxc, "ufw allow from 192.168.122.0/24 to any port 22 proto tcp comment 'SSH desde el anfitrion'"]
  - [bash, -euxc, "ufw allow in on tailscale0 comment 'Tailscale'"]
  - [bash, -euxc, "ufw --force enable"]
  - [bash, -euxc, "touch /var/lib/biohub-cloudinit-ok"]

power_state:
  mode: reboot
  message: "cloud-init terminado, reiniciando"
  timeout: 120
  condition: true
