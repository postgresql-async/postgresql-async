#!/usr/bin/env bash
set -euo pipefail

main () {
    # Make a copy of certdir so we can edit the files
    cp -r /custom/conf /home/conf
    cp -r /custom/cert /home/cert
    chown postgres:postgres /home/cert/*.key
    chmod 0600 /home/cert/*.key

    local pg_opts=""
    add_pg_opt () {
        pg_opts="${pg_opts} ${1}"
    }


    # Customize pg_hba.conf
    local pg_hba="/home/conf/pg_hba.conf"
    sed -i 's/127.0.0.1\/32/0.0.0.0\/0/g' "${pg_hba}"
    add_pg_opt "-c hba_file=${pg_hba}"
    add_pg_opt "-c ssl=on"
    add_pg_opt "-c ssl_cert_file=/home/cert/server.crt"
    add_pg_opt "-c ssl_key_file=/home/cert/server.key"

    echo "Starting postgres version $PG_MAJOR with options: ${pg_opts} $@"

    local entrypoint_script
    if [[ -x /docker-entrypoint.sh ]]; then
        entrypoint_script='/docker-entrypoint.sh'
    elif [[ -x /usr/local/bin/docker-entrypoint.sh ]]; then
        # On 13+ the script is not longer symlinked to the root
        entrypoint_script='/usr/local/bin/docker-entrypoint.sh'
    else
        err "Could not find postgres container entry point script"
    fi
    exec "${entrypoint_script}" "$@" ${pg_opts}
}

main "$@"
