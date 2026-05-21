# Jitsi JaaS Setup

This project uses the official 8x8 JaaS JWT flow.

## Current Local Files

- Private key: `tmp/jitsi-jaas/jitsi-private-key.pem`
- Public key to upload: `tmp/jitsi-jaas/jitsi-public-key.pem`
- Local Docker env file: `.env`

`tmp/` and `.env` are git-ignored. Do not commit private keys.

## 8x8 Console Steps

1. Open the 8x8 JaaS console.
2. Create or select a JaaS app and copy its App ID.
3. Go to API Keys.
4. Add your own key and upload `tmp/jitsi-jaas/jitsi-public-key.pem`.
5. Copy the generated Key ID (`kid`).
6. Edit `.env`:
   - Replace `PASTE_8X8_JAAS_APP_ID` with the App ID.
   - Replace `PASTE_8X8_JAAS_KEY_ID` with the Key ID.
   - Leave `JITSI_JAAS_PRIVATE_KEY_PEM` as the generated private key.

## Apply Locally

After `.env` contains the real App ID and Key ID:

```powershell
docker compose up -d --build backend
```

Then test:

1. Log in as `student.jitsi@haru.test` / `Password123!`.
2. Open `http://localhost:3000/tutors/7`.
3. Choose the near-term slot and click `25분 수업 예약하기`.
4. Open `내 예약`.
5. Click `수업방 열기`.

## References

- 8x8 JaaS API Keys: https://developer.8x8.com/jaas/v5/docs/jaas-console-api-keys
- Generating and adding a JaaS API key: https://developer.8x8.com/jaas/docs/api-keys-generate-add
- Jitsi IFrame API: https://jitsi.github.io/handbook/docs/dev-guide/dev-guide-iframe/
