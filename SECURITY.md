Security notes
==============

What is protected, what is not, and how to run the API safely. Economofar is a portfolio project, but these are the points that a deployment would need.

Secrets
-------

- The key that signs the sign-in tokens (JWT) is not in the code. The API reads it from JWT_SECRET (an environment variable or the .env file of economorfar_api). It must be Base64 and hold at least 32 bytes. To make one: openssl rand -base64 48
- When JWT_SECRET is missing or unusable, the API starts with a temporary key and logs a warning. It works, but every sign-in is lost when the server restarts. For a deployment, always set JWT_SECRET.
- A token lasts 168 hours (7 days) unless JWT_EXPIRATION_HOURS says otherwise. Changing the key signs everybody out.
- The .env file holds the database password and the key, so it must never be committed. economorfar_api/.gitignore ignores it, and ignores the uploads folder too. If a .env file was committed once, change the passwords and the key: Git keeps the history.
- Before the project goes to a public repository, check that application.properties and the other settings files hold no password or key (use environment variables instead). The values of docker-compose.yml are for local work only: do not use them on a server.

Sign-in
-------

- Passwords are hashed with BCrypt. The API is stateless: no session and no cookie. Every address except /api/auth (and /error) needs the header Authorization: Bearer followed by the token.
- A token that is missing, expired, altered, signed with another key, or made for an account that no longer exists is not accepted. The request goes on as anonymous and answers 401, and the app takes that answer as the end of the session and goes back to the login screen.
- The app keeps the token in the iOS Keychain. It can be read only after the first unlock of the phone, and it does not move to another device with a backup.
- Five wrong passwords for the same e-mail address (capital letters and spaces around it do not matter) block that address for 15 minutes, counted from the first failure: the API answers 429, even to the right password. A correct sign-in clears the count. An address that has no account is counted the same way, so the answers do not tell whether an account exists. The counts live in the memory of the server: a restart clears them, and the table keeps at most 10,000 addresses (when it is full the expired ones go first, then the ones that are not locked, then the oldest locked ones, and the address being recorded is never dropped).
- At sign up the API checks the first and last name (100 characters at most), an e-mail address that looks like one (it is stored in lower case), and a password of 8 to 72 characters (72 bytes at most: BCrypt reads no more, and an accented letter counts 2) with at least one letter and one digit. A very common password (password1, azerty123 and the like) and a password that holds the part of the e-mail before the @ are refused.
- A password change in the profile follows the same rules as the sign-up. Five wrong current passwords in 15 minutes block the change for that account (answer 429), the same way as the sign-in lockout, so a stolen token cannot be used to guess the current password.

Uploads
-------

- An uploaded file must be a JPEG, PNG, GIF, WebP or HEIC image, 15 MB at most. It is recognised by its first bytes, not by its name or by what the client says. Any other file answers 415, an empty file 400 and a file that is too large 413. The check runs on the ticket upload and on /api/images/upload.
- The file is stored under a random name made of an identifier and an extension. The name that the client sends is never used, and an extension that is not an image extension becomes jpg.
- The images are served from /uploads and need the token like every other address. The app loads them with the token, and sends it only to the server address, never to another host.
- The server has its own size limit too (spring.servlet.multipart.max-file-size in application.properties). The smaller of the two limits applies.

Docker
------

- The container runs the API as a user without rights, not as root. The .env file, the uploads folder, the Git folder and the tests are kept out of the image by .dockerignore, so a key or a receipt cannot end up in an image that you push.
- The key of the tokens is given when the container starts (JWT_SECRET, from the .env file or the environment). It is never written in the image.
- PostgreSQL is published on this computer only (127.0.0.1:5433). The API is published on port 8080 for every computer of the network, on purpose, so that a phone can reach it.
- docker-compose.yml turns the demo data on by default, and the password of the demo account is public. For a server, start with ECONOMOFAR_PROFILE=none, and give the database its own password: the one of the file is for local work.

Cross-origin calls
------------------

- The wildcard CORS setting that two controllers had was removed. A web page from another origin can no longer call the API. The iPhone app is not affected.

Known limits
------------

- The lockout counts by e-mail address, not by network address. Someone who knows an address can lock it for 15 minutes (the price of this rule), and a script that tries many different addresses is not slowed down. Someone who locks about 10,000 addresses of their own (about 50,000 requests) can push a locked address out of the table and get 5 more tries. A limit by network address belongs to the reverse proxy in front of the API.
- The accounts that exist keep their password, even if it would not pass the rules today. The e-mail address is not confirmed and there is no password reset yet.
- A token cannot be revoked before it expires. Signing out only removes it from the phone.
- An image is private to the signed in users, not to its owner alone: anyone signed in who knows the random name of a file can fetch it. The tickets that hold the name are shown only to their owner.
- The files that were uploaded before this step are still in the uploads folder and are still served to every signed in user as they were. Delete the ones that are not images (an .html or .svg file, for instance).
- The API speaks plain HTTP. For a deployment, put it behind HTTPS (a reverse proxy) and change the server address of the app (Settings, or Core/Config/AppConfig.swift).
- If the project has a demo account, its password is public on purpose: do not enable the demo data on a server.
