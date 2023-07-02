# SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
#
# SPDX-License-Identifier: MIT

import asyncio
import calendar
import sys
from asyncio import TaskGroup, Task, FIRST_COMPLETED
from dataclasses import dataclass
from datetime import date
from pathlib import PurePath
from typing import Iterator

import aiofiles
import aiohttp
from termcolor import cprint

base_url = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/"
#base_url = "https://api.nasa.gov/DONKI/"
api_key = None
datasets_dir = PurePath(__file__).parent / "resources/org/equeim/spacer/donki/data/network/datasets"

years = [
    2016,
    2017,
    2018,
    2019,
    2020,
    2021,
    2022,
    2023
]

events = [
    "CME",
    "IPS",
    "RBE",
    "HSS",
    "GST",
    "FLR",
    "SEP",
    "MPC"
]


@dataclass
class DownloadParameters:
    year: int
    month: int
    event: str


async def download(params: DownloadParameters, session: aiohttp.ClientSession):
    start_date = date(params.year, params.month, 1)
    end_date = date(params.year, params.month, calendar.monthrange(params.year, params.month)[1])
    url = f"{base_url}{params.event}?startDate={start_date}&endDate={end_date}"
    if api_key:
        url += f"&api_key={api_key}"
    print(f"url = {url}")
    try:
        async with session.get(url) as response:
            response.raise_for_status()
            try:
                print("X-RateLimit-Remaining: {}".format(response.headers["X-RateLimit-Remaining"]))
            except KeyError:
                pass
            data = await response.read()
            if data:
                file_name = "{}_{}_{}.json".format(params.event, start_date, end_date)
                file_path = datasets_dir / file_name
                try:
                    async with aiofiles.open(file_path, "wb") as f:
                        await f.write(data)
                except OSError as e:
                    cprint(f"Nope {file_path}: {repr(e)}", "red", attrs=["bold"], file=sys.stderr)
    except (aiohttp.ClientError, asyncio.TimeoutError) as e:
        cprint(f"Nope {url}: {repr(e)}", "red", attrs=["bold"], file=sys.stderr)


def download_parameters() -> Iterator[DownloadParameters]:
    for year in years:
        for month in range(1, 13):
            for event in events:
                yield DownloadParameters(year, month, event)


async def main():
    params = download_parameters()
    max_parallel_tasks = 5

    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=30)) as session:
        async with TaskGroup() as tg:
            parallel_tasks: set[Task] = set()
            while True:
                try:
                    while len(parallel_tasks) < max_parallel_tasks:
                        parallel_tasks.add(tg.create_task(download(next(params), session)))
                except StopIteration:
                    break
                done, pending = await asyncio.wait(parallel_tasks, return_when=FIRST_COMPLETED)
                parallel_tasks = pending
            await asyncio.wait(parallel_tasks)


if __name__ == "__main__":
    asyncio.run(main())
