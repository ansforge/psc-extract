project = "prosanteconnect/pscextract"

# Labels can be specified for organizational purposes.
labels = { "domaine" = "psc" }

runner {
    enabled = true
    data_source "git" {
        url = "https://github.com/prosanteconnect/pscextract.git"
        ref = var.datacenter
    }
    poll {
        enabled = true
    }
}

# An application to deploy.
app "prosanteconnect/pscextract" {
  # the Build step is required and specifies how an application image should be built and published. In this case,
  # we use docker-pull, we simply pull an image as is.
  build {
    use "docker" {
      build_args = {
        "proxy_address" = var.proxy_address
      }
      dockerfile = "${path.app}/${var.dockerfile_path}"
    }
    # Uncomment below to use a remote docker registry to push your built images.
    registry {
      use "docker" {
        image = "prosanteconnect/psc-ws-maj"
        tag   = gitrefpretty()
        encoded_auth = filebase64("/secrets/dockerAuth.json")
      }
    }
  }

  # Deploy to Nomad
  deploy {
    use "nomad-jobspec" {
      jobspec = templatefile("${path.app}/pscextract.nomad.tpl", {
        datacenter = var.datacenter
        image = var.image
        tag = var.tag
      })
    }
  }
}

variable "datacenter" {
  type    = string
  default = "dc1"
}

variable "dockerfile_path" {
  type = string
  default = "Dockerfile"
}

variable "proxy_address" {
  type = string
  default = "proxy_address"
}
