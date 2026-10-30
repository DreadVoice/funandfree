const ORIGIN = "https://funandfree.duckdns.org";

export default {
  async fetch(request) {
    const incoming = new URL(request.url);
    const target = new URL(incoming.pathname + incoming.search, ORIGIN);

    if (incoming.pathname.startsWith("/actuator")) {
      return new Response("not found", { status: 404 });
    }

    const headers = new Headers(request.headers);
    headers.delete("host");
    headers.set("X-Forwarded-Proto", "https");

    const hasBody = request.method !== "GET" && request.method !== "HEAD";

    let upstream;
    try {
      upstream = await fetch(target, {
        method: request.method,
        headers,
        body: hasBody ? request.body : undefined,
        redirect: "manual",
      });
    } catch (error) {
      return new Response(`origin unreachable: ${error}`, { status: 502 });
    }

    const response = new Response(upstream.body, upstream);
    response.headers.delete("transfer-encoding");
    return response;
  },
};
