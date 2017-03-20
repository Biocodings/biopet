#! /usr/bin/env python2
from jinja2 import Environment, FileSystemLoader
import os
import argparse


if __name__ == "__main__"

parser=argparse.ArgumentParser(description="This is the

j2_env = Environment(loader=FileSystemLoader(os.getcwd()))
rendered = j2_env.get_template(
    "not_yet_documented_tools.j2").render(
        biopet_dir="~/biopet",
        tools_dir="floberdy",
        docs_dir="flopsy")
output_file=open("test.sh", "w")
output_file.seek(0)
output_file.write(rendered)
output_file.close()
