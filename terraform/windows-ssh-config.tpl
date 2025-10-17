add-content -path c:/users/sebas/.ssh/config -value @'

Host ${hostname}
    HostName ${hostname}
    User ${user}
    IdentityFile ${identity_file}
'@