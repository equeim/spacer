# SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
#
# SPDX-License-Identifier: MIT

import sys

if sys.version_info < (3, 11):
    print("Minimum supported Python version is 3.11", file=sys.stderr)
    exit(1)

import asyncio
import calendar
from asyncio import TaskGroup, Semaphore
from dataclasses import dataclass
from datetime import date
from pathlib import PurePath
from typing import Iterator

import aiofiles
import aiohttp
from termcolor import cprint

base_url = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/"
# base_url = "https://api.nasa.gov/DONKI/"
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

parallel_downloads_semaphore = Semaphore(5)
request_timeout_seconds = 30
rate_limit_remaining_header = "X-RateLimit-Remaining"


def exception_to_string(e: Exception) -> str:
    message = str(e)
    if message:
        return f"{e.__class__.__name__}: {message}"
    return e.__class__.__name__


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
    async with parallel_downloads_semaphore:
        print(f"url = {url}")
        try:
            async with session.get(url) as response:
                response.raise_for_status()
                try:
                    print(f"{rate_limit_remaining_header}: {response.headers[rate_limit_remaining_header]}")
                except KeyError:
                    pass
                data = await response.read()
        except (aiohttp.ClientError, asyncio.TimeoutError) as e:
            cprint(f"Nope for {url}: {exception_to_string(e)}", "red", attrs=["bold"], file=sys.stderr)
            return
    if not data:
        return
    file_name = "{}_{}_{}.json".format(params.event, start_date, end_date)
    file_path = datasets_dir / file_name
    try:
        async with aiofiles.open(file_path, "wb") as f:
            await f.write(data)
    except OSError as e:
        cprint(f"Nope for {file_path}: {exception_to_string(e)}", "red", attrs=["bold"], file=sys.stderr)


def download_parameters() -> Iterator[DownloadParameters]:
    for year in years:
        for month in range(1, 13):
            for event in events:
                yield DownloadParameters(year, month, event)


async def main():
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=request_timeout_seconds)) as session:
        async with TaskGroup() as tg:
            for params in download_parameters():
                tg.create_task(download(params, session))


if __name__ == "__main__":
    asyncio.run(main())
