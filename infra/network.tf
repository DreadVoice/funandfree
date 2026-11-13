resource "google_compute_firewall" "ssh_from_iap" {
  name          = "allow-ssh-from-iap"
  description   = "SSH via Identity-Aware Proxy only"
  network       = "default"
  direction     = "INGRESS"
  priority      = 1000
  source_ranges = ["35.235.240.0/20"]
  target_tags   = [var.network_tag]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}

resource "google_compute_firewall" "web_public" {
  name          = "allow-web-public"
  description   = "HTTP and HTTPS from the public internet over IPv6"
  network       = "default"
  direction     = "INGRESS"
  priority      = 1000
  source_ranges = ["::/0"]
  target_tags   = [var.network_tag]

  allow {
    protocol = "tcp"
    ports    = ["80"]
  }

  allow {
    protocol = "tcp"
    ports    = ["443"]
  }
}
