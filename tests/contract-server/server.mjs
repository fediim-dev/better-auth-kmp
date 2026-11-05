// Ephemeral better-auth server for the better-auth-core jvmTest contract suite.
// Protocol: prints exactly one `PORT=<n>` line once serving; exits on stdin EOF or SIGTERM.
import { randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { DatabaseSync } from "node:sqlite";
import { apiKey } from "@better-auth/api-key";
import { passkey } from "@better-auth/passkey";
import { betterAuth } from "better-auth";
import { getMigrations } from "better-auth/db/migration";
import { toNodeHandler } from "better-auth/node";
import { bearer, organization, twoFactor } from "better-auth/plugins";

let handler = (_req, res) => {
  res.statusCode = 503;
  res.end();
};
const server = createServer((req, res) => handler(req, res));
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const baseURL = `http://127.0.0.1:${server.address().port}`;

const options = {
  baseURL,
  secret: randomBytes(32).toString("hex"),
  database: new DatabaseSync(":memory:"),
  emailAndPassword: { enabled: true, requireEmailVerification: false },
  rateLimit: { enabled: false },
  telemetry: { enabled: false },
  user: {
    additionalFields: { role: { type: "string", defaultValue: "member", input: false } },
  },
  plugins: [
    bearer(),
    organization(),
    twoFactor(),
    passkey({ rpID: "localhost", origin: baseURL }),
    apiKey(),
  ],
};
const { runMigrations } = await getMigrations(options);
await runMigrations();
handler = toNodeHandler(betterAuth(options));

console.log(`PORT=${server.address().port}`);

// Orphan guard: the JVM holds our stdin; when it dies the pipe closes and we exit.
process.stdin.resume();
process.stdin.on("end", () => process.exit(0));
process.stdin.on("close", () => process.exit(0));
