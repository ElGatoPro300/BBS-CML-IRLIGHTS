"""Build and run the native Outline/replay UI regression for one target."""
import argparse
from client_check import run_check

parser = argparse.ArgumentParser()
parser.add_argument("--mc")
args = parser.parse_args()
run_check(args.mc, "replay-ui", "tools/replays/ReplayFilmContextClientCheck.java", "ReplayFilmContextClientCheck", "PASS: replay film context client regression")
