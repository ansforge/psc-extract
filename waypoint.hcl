project = "prosanteconnect/${workspace.name}/pscextract"

# Labels can be specified for organizational purposes.
labels = { "domaine" = "psc" }

runner {
    enabled = true
    profile = "secpsc-${workspace.name}"
    data_source "git" {
        url = "https://github.com/prosanteconnect/pscextract.git"
        ref = "${workspace.name}"
    }
    poll {
        enabled = false
    }
}

# An application to deploy.
app "prosanteconnect/pscextract" {
  build {
    use "docker" {
      build_args = {"PROSANTECONNECT_PACKAGE_GITHUB_TOKEN"="${var.github_token}"}
      dockerfile = "${path.app}/${var.dockerfile_path}"
      disable_entrypoint = true
    }
    # Uncomment below to use a remote docker registry to push your built images.
    registry {
      use "docker" {
        image = "${var.registry_username}/pscextract"
        tag   = gitrefpretty()
        username = var.registry_username
        password = var.registry_password
        local = true
      }
    }
  }

  # Deploy to Nomad
  deploy {
    use "nomad-jobspec" {
      jobspec = templatefile("${path.app}/pscextract.nomad.tpl", {
        datacenter = var.datacenter
        nomad_namespace = var.nomad_namespace
        registry_username = var.registry_username
      })
    }
  }
}

variable "datacenter" {
  type = string
  default = ""
  env = ["NOMAD_DATACENTER"]
}

variable "nomad_namespace" {
  type = string
  default = ""
  env = ["NOMAD_NAMESPACE"]
}

variable "registry_username" {
  type    = string
  default = ""
  env     = ["REGISTRY_USERNAME"]
  sensitive = true
}

variable "registry_password" {
  type    = string
  default = ""
  env     = ["REGISTRY_PASSWORD"]
  sensitive = true
}

variable "dockerfile_path" {
  type = string
  default = "Dockerfile"
}

variable "proxy_address" {
  type = string
  default = ""
}

variable "registry_path" {
  type = string
  default = "registry.repo.proxy-dev-forge.asip.hst.fluxus.net/prosanteconnect"
}

variable "github_token" {
  type    = string
  default = ""
  env     = ["PROSANTECONNECT_PACKAGE_GITHUB_TOKEN"]
  sensitive = true
}
