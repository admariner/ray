// Copyright 2022 The Ray Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "ray/gcs/gcs_client/usage_stats_client.h"

namespace ray {
namespace gcs {
UsageStatsClient::UsageStatsClient(const std::string &gcs_address,
                                   ClusterID cluster_id,
                                   instrumented_io_context &io_service) {
  // This client lives in GCS itself so it must not allow nil cluster_id.
  GcsClientOptions options(gcs_address,
                           cluster_id,
                           /*allow_cluster_id_nil=*/false,
                           /*fetch_cluster_id_if_nil=*/false);
  gcs_client_ = std::make_unique<GcsClient>(options);
  RAY_CHECK_OK(gcs_client_->Connect(io_service));
}

void UsageStatsClient::RecordExtraUsageTag(usage::TagKey key, const std::string &value) {
  RAY_CHECK_OK(gcs_client_->InternalKV().AsyncInternalKVPut(
      kUsageStatsNamespace,
      kExtraUsageTagPrefix + absl::AsciiStrToLower(usage::TagKey_Name(key)),
      value,
      /*overwrite=*/true,
      GetGcsTimeoutMs(),
      [](Status status, std::optional<int> added_num) {
        if (!status.ok()) {
          RAY_LOG(DEBUG) << "Failed to put extra usage tag, status = " << status;
        }
      }));
}

void UsageStatsClient::RecordExtraUsageCounter(usage::TagKey key, int64_t counter) {
  RecordExtraUsageTag(key, std::to_string(counter));
}
}  // namespace gcs
}  // namespace ray
