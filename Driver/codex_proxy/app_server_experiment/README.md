# Codex App Server Experiment

This folder contains a postponed transport experiment. It is not part of the
current Seeker workflow and nothing in the active proxy imports it.

## Goal

The experiment tried to replace repeated `codex exec` processes with one
shared Codex App Server while preserving the same proxy boundary, result
contract, and token metrics.

## Outcome

The transport worked only partially. Nested analyzer orchestration did not
complete reliably, the lifecycle code introduced substantial complexity, and
Windows displayed repeated process, firewall, and permission prompts. A full
pipeline run through this backend was not successfully verified, so the active
proxy continues to use `codex_proxy/codex_cli.py`.

## Contents

- `server.py`: starts and stops the local App Server process.
- `server_app.py`: experimental transport facade.
- `app_server.py`: Codex thread/turn client.
- `json_rpc.py`: WebSocket JSON-RPC adapter.
- `config.ini`: settings used only by this experiment.
- `requirements.txt`: dependency used only by this experiment.

To inspect the experiment manually, install its isolated dependency with
`python -m pip install -r codex_proxy/app_server_experiment/requirements.txt`.
Do not connect it to the main workflow without an explicit backend switch and
an end-to-end pipeline test.
