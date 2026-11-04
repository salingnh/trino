#!/usr/bin/env bash
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail
mkdir -p /data/tmp
exec java -Xmx6G -XX:+ExitOnOutOfMemoryError \
  --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
  --sun-misc-unsafe-memory-access=allow -Djdk.attach.allowAttachSelf=true \
  -Djava.io.tmpdir=/data/tmp -Dconfig=/etc/trino/config.properties \
  -Dlog.path=/data/server.log -Dlog.levels-file=/etc/trino/log.properties \
  -cp '/workspace/core/trino-server-main/target/trino-server-main-484-SNAPSHOT.jar:/workspace/core/trino-server-main/target/runtime-deps/*' \
  io.trino.server.TrinoServer
