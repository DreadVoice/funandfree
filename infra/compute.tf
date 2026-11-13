resource "google_compute_instance" "app" {
  name         = var.instance_name
  machine_type = var.machine_type
  zone         = var.zone
  tags         = [var.network_tag]

  boot_disk {
    auto_delete = true

    initialize_params {
      image = var.boot_image
      size  = var.boot_disk_size_gb
      type  = var.boot_disk_type
    }
  }

  network_interface {
    network    = "default"
    subnetwork = "default"
    stack_type = "IPV4_IPV6"

    ipv6_access_config {
      network_tier = "PREMIUM"
    }
  }

  service_account {
    email  = google_service_account.vm_runtime.email
    scopes = ["cloud-platform"]
  }

  metadata = {
    enable-oslogin = "TRUE"
  }

  scheduling {
    automatic_restart   = true
    on_host_maintenance = "MIGRATE"
    preemptible         = false
    provisioning_model  = "STANDARD"
  }

  deletion_protection = false

  lifecycle {
    prevent_destroy = true

    ignore_changes = [
      boot_disk[0].initialize_params[0].image,
      metadata["ssh-keys"],
    ]
  }
}
