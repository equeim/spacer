# SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
#
# SPDX-License-Identifier: MIT

import os
import subprocess
import sys

years = [
    "2016",
    "2017",
    "2018",
    "2019",
    "2020",
    "2021",
    "2022",
]

base_url = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/"
datasets_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), "resources/org/equeim/spacer/donki/data/network")

def download(year, month_start, day_start, month_end, day_end, event):
    url = "{}{}?startDate={}-{}-{}&endDate={}-{}-{}".format(base_url, event, year, month_start, day_start, year, month_end, day_end)
    print("url = " + url)
    file_name = "{}_{}-{}-{}_{}-{}-{}.json".format(event, year, month_start, day_start, year, month_end, day_end)
    subprocess.run(["curl", "--output", os.path.join(datasets_dir, file_name), url], check=True)

months = [
    "01",
    "04",
    "07",
    "10"
]

events = [
    "CME",
    "IPS",
    "RBE",
    "HSS"
]

day_start = "01"
day_end = "07"

for year in years:
    for month in months:
        for event in events:
            download(year, month, day_start, month, day_end, event)

months = [
    ("01", "04"),
    ("07", "10")
]

events = [
    "GST",
    "FLR",
    "SEP",
    "MPC"
]

day_start = "01"
day_end = "28"

""" for year in years:
    for months_pair in months:
        for event in events:
            download(year, months_pair[0], day_start, months_pair[1], day_end, event) """
