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
import { createAuthMiddleware } from "better-auth/api";
import { bearer, oauthPopup, oneTimeToken, organization, twoFactor } from "better-auth/plugins";

// Test-only OAuth provider behind the built-in `gitlab` social provider (issuer = /mock-gitlab).
// A `scope` containing "deny" makes the authorize step answer access_denied.
const mockAccessToken = randomBytes(24).toString("hex");
function mockProvider(req, res) {
  const url = new URL(req.url, "http://127.0.0.1");
  const json = (body) => {
    res.setHeader("content-type", "application/json");
    res.end(JSON.stringify(body));
  };
  if (url.pathname === "/mock-gitlab/oauth/authorize") {
    const redirectUri = url.searchParams.get("redirect_uri");
    if (!redirectUri) {
      res.statusCode = 400;
      res.end();
      return;
    }
    const target = new URL(redirectUri);
    if ((url.searchParams.get("scope") ?? "").includes("deny")) {
      target.searchParams.set("error", "access_denied");
    } else {
      target.searchParams.set("code", "mock-code");
    }
    target.searchParams.set("state", url.searchParams.get("state") ?? "");
    res.statusCode = 302;
    res.setHeader("location", target.toString());
    res.end();
  } else if (url.pathname === "/mock-gitlab/oauth/token") {
    req.resume();
    json({ access_token: mockAccessToken, token_type: "Bearer" });
  } else if (url.pathname === "/mock-gitlab/api/v4/user") {
    json({ id: 4242, email: "social-user@example.com", name: "Social User", state: "active" });
  } else {
    res.statusCode = 404;
    res.end();
  }
}

// Receivers that may get `ott=<one-time-token>` appended to the /callback/ redirect (plan L20).
// Kept separate from trustedOrigins: with skipStateCookieCheck an attacker controls callbackURL,
// so appending to every trusted frontend could leak a victim's token via Referer or history.
// http(s) entries need the parsed origin/host and the exact normalized path; custom app schemes
// match by scheme.
function receiverAllowed(location, receivers) {
  let url;
  try {
    url = new URL(location);
  } catch {
    return false;
  }
  return receivers.some((r) => {
    if (r.scheme) {
      if (r.scheme === "http" || r.scheme === "https") return false;
      return url.protocol === `${r.scheme}:`;
    }
    if (r.loopback) {
      return url.protocol === "http:" && url.hostname === "127.0.0.1" && url.pathname === r.path;
    }
    return url.origin === r.origin && url.pathname === r.path;
  });
}

const nativeCallbackOtt = ({ receivers }) => ({
  id: "native-callback-ott",
  hooks: {
    after: [
      {
        matcher: () => true,
        handler: createAuthMiddleware(async (ctx) => {
          const headers = ctx.context.responseHeaders;
          const ott = headers?.get("set-ott");
          headers?.delete("set-ott"); // never expose the token as a response header
          if (!ctx.path?.startsWith("/callback/")) return;
          const location = headers?.get("location");
          if (!ott || !location || !receiverAllowed(location, receivers)) return;
          const next = new URL(location);
          next.searchParams.set("ott", ott);
          ctx.setHeader("location", next.toString());
        }),
      },
    ],
  },
});

let handler = (_req, res) => {
  res.statusCode = 503;
  res.end();
};
const server = createServer((req, res) => {
  if (req.url?.startsWith("/mock-gitlab/")) return mockProvider(req, res);
  handler(req, res);
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
const baseURL = `http://127.0.0.1:${server.address().port}`;

const options = {
  baseURL,
  secret: randomBytes(32).toString("hex"),
  database: new DatabaseSync(":memory:"),
  emailAndPassword: { enabled: true, requireEmailVerification: false },
  account: { skipStateCookieCheck: true },
  trustedOrigins: ["http://127.0.0.1:*"],
  socialProviders: {
    gitlab: {
      clientId: "mock-client",
      clientSecret: randomBytes(16).toString("hex"),
      issuer: `${baseURL}/mock-gitlab`,
    },
  },
  rateLimit: { enabled: false },
  telemetry: { enabled: false },
  user: {
    additionalFields: { role: { type: "string", defaultValue: "member", input: false } },
  },
  plugins: [
    bearer(),
    oneTimeToken({ setOttHeaderOnNewSession: true }),
    nativeCallbackOtt({
      receivers: [
        { loopback: true, path: "/auth/callback" },
        { origin: baseURL, path: "/sdk-landing" },
      ],
    }),
    oauthPopup(),
    organization(),
    twoFactor(),
    passkey({ rpID: "localhost", origin: baseURL }),
    apiKey({ enableMetadata: true, enableSessionForAPIKeys: true }),
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
