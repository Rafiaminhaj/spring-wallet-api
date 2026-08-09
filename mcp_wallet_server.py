import sys
import json
import urllib.request
import urllib.error

# API Config
API_BASE = "http://localhost:8080/api"

def log_debug(msg):
    sys.stderr.write(f"[DEBUG] {msg}\n")
    sys.stderr.flush()

def make_request(url, method="GET", headers=None, data=None):
    if headers is None:
        headers = {}
    
    headers["User-Agent"] = "Wallet-MCP-Server/1.0"
    if data:
        headers["Content-Type"] = "application/json"
        encoded_data = json.dumps(data).encode("utf-8")
    else:
        encoded_data = None

    req = urllib.request.Request(url, method=method, headers=headers, data=encoded_data)
    
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except Exception as e:
        return 500, str(e)

# List of exposed MCP tools
TOOLS = [
    {
        "name": "wallet_register",
        "description": "Register a new user account and initialize a free $1000 credit wallet.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "username": {"type": "string", "description": "The unique username for the account."},
                "password": {"type": "string", "description": "Account password."}
            },
            "required": ["username", "password"]
        }
    },
    {
        "name": "wallet_login",
        "description": "Authenticate user credentials and retrieve the session JWT authentication token.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "username": {"type": "string", "description": "Account username."},
                "password": {"type": "string", "description": "Account password."}
            },
            "required": ["username", "password"]
        }
    },
    {
        "name": "wallet_get_balance",
        "description": "Check the available wallet balance of the authenticated user. Requires a valid JWT token.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "jwt_token": {"type": "string", "description": "The JWT Bearer token acquired from login."}
            },
            "required": ["jwt_token"]
        }
    },
    {
        "name": "wallet_transfer",
        "description": "Transfer funds securely to another user's wallet. Requires a valid JWT token.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "target_username": {"type": "string", "description": "The recipient's username."},
                "amount": {"type": "number", "description": "The amount to transfer (must be at least 0.01)."},
                "description": {"type": "string", "description": "Transfer memo/reason."},
                "jwt_token": {"type": "string", "description": "The JWT Bearer token acquired from login."}
            },
            "required": ["target_username", "amount", "jwt_token"]
        }
    },
    {
        "name": "wallet_get_transactions",
        "description": "Fetch the transaction ledger audit history for the wallet. Requires a valid JWT token.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "jwt_token": {"type": "string", "description": "The JWT Bearer token acquired from login."}
            },
            "required": ["jwt_token"]
        }
    }
]

def handle_tool_call(name, args):
    if name == "wallet_register":
        code, resp = make_request(
            f"{API_BASE}/auth/register", 
            method="POST", 
            data={"username": args.get("username"), "password": args.get("password")}
        )
        return resp

    elif name == "wallet_login":
        code, resp = make_request(
            f"{API_BASE}/auth/login", 
            method="POST", 
            data={"username": args.get("username"), "password": args.get("password")}
        )
        return resp

    elif name == "wallet_get_balance":
        token = args.get("jwt_token")
        code, resp = make_request(
            f"{API_BASE}/wallet/balance", 
            headers={"Authorization": f"Bearer {token}"}
        )
        if code == 200:
            wallet = json.loads(resp)
            return f"Wallet ID: #{wallet['id']}\nOwner: {wallet['user']['username']}\nBalance: ${wallet['balance']:.2f}"
        return f"Error ({code}): {resp}"

    elif name == "wallet_transfer":
        token = args.get("jwt_token")
        code, resp = make_request(
            f"{API_BASE}/wallet/transfer", 
            method="POST",
            headers={"Authorization": f"Bearer {token}"},
            data={
                "targetUsername": args.get("target_username"),
                "amount": args.get("amount"),
                "description": args.get("description", "MCP Transfer")
            }
        )
        return resp

    elif name == "wallet_get_transactions":
        token = args.get("jwt_token")
        code, resp = make_request(
            f"{API_BASE}/wallet/transactions", 
            headers={"Authorization": f"Bearer {token}"}
        )
        if code == 200:
            txs = json.loads(resp)
            if not txs:
                return "No transactions found in ledger."
            lines = []
            for t in txs:
                src = t["sourceWallet"]["user"]["username"] if t["sourceWallet"] else "SYSTEM"
                dest = t["targetWallet"]["user"]["username"]
                lines.append(f"[{t['timestamp']}] {src} sent ${t['amount']:.2f} to {dest} (Memo: {t['description']})")
            return "\n".join(lines)
        return f"Error ({code}): {resp}"

    else:
        return f"Unknown tool: {name}"

def main():
    log_debug("MCP Server started.")
    
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
                
            req = json.loads(line.strip())
            msg_id = req.get("id")
            method = req.get("method")
            params = req.get("params", {})

            # Respond to JSON-RPC methods
            if method == "initialize":
                res = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {
                            "tools": {}
                        },
                        "serverInfo": {
                            "name": "wallet-mcp-server",
                            "version": "1.0.0"
                        }
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "tools/list":
                res = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {
                        "tools": TOOLS
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "tools/call":
                tool_name = params.get("name")
                tool_args = params.get("arguments", {})
                
                try:
                    output = handle_tool_call(tool_name, tool_args)
                except Exception as e:
                    output = f"Execution error: {e}"

                res = {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": output
                            }
                        ]
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()
                
        except Exception as e:
            log_debug(f"Exception loop: {e}")

if __name__ == "__main__":
    main()
