#pragma once
#include <string>
#include <vector>
#include <cstring>
#include <cctype>
#include "esp_mac.h"
#include "esp_gap_ble_api.h"

static bool s2_parse_mac(const std::string &s, uint8_t out[6]) {
  unsigned v[6];
  if (sscanf(s.c_str(), "%x:%x:%x:%x:%x:%x", &v[0], &v[1], &v[2], &v[3], &v[4], &v[5]) != 6) return false;
  for (int i = 0; i < 6; i++) { if (v[i] > 255) return false; out[i] = (uint8_t) v[i]; }
  return true;
}

static std::string s2_fmt_mac(const uint8_t m[6]) {
  char b[18];
  snprintf(b, sizeof(b), "%02X:%02X:%02X:%02X:%02X:%02X", m[0], m[1], m[2], m[3], m[4], m[5]);
  return b;
}

static std::vector<uint8_t> s2_parse_hex(const std::string &s) {
  std::string h;
  std::string t = s;
  for (auto &c : t) c = tolower(c);
  size_t p;
  while ((p = t.find("0x")) != std::string::npos) t.erase(p, 2);
  for (char c : t) if (isxdigit((unsigned char) c)) h += c;
  std::vector<uint8_t> o;
  for (size_t i = 0; i + 1 < h.size(); i += 2) o.push_back((uint8_t) strtol(h.substr(i, 2).c_str(), nullptr, 16));
  return o;
}

// Returns manufacturer data starting with 53 05 (Nintendo company ID, little-endian).
// Accepts a full raw advertisement, manufacturer data, or data without the company ID.
static std::vector<uint8_t> s2_payload(const std::string &hex) {
  auto raw = s2_parse_hex(hex);
  size_t i = 0;
  while (i < raw.size()) {
    uint8_t ln = raw[i];
    if (ln == 0 || i + 1 + ln > raw.size()) break;
    if (raw[i + 1] == 0xFF && ln >= 3 && raw[i + 2] == 0x53 && raw[i + 3] == 0x05)
      return std::vector<uint8_t>(raw.begin() + i + 2, raw.begin() + i + 1 + ln);
    i += 1 + ln;
  }
  if (raw.size() >= 2 && raw[0] == 0x53 && raw[1] == 0x05) return raw;
  std::vector<uint8_t> o = {0x53, 0x05};
  o.insert(o.end(), raw.begin(), raw.end());
  return o;
}

static esp_err_t s2_start_adv(const std::vector<uint8_t> &payload) {
  uint8_t adv[31];
  size_t n = 0;
  if (payload.size() + 5 > 31) return ESP_ERR_INVALID_SIZE;
  adv[n++] = 0x02; adv[n++] = 0x01; adv[n++] = 0x06;
  adv[n++] = payload.size() + 1; adv[n++] = 0xFF;
  memcpy(adv + n, payload.data(), payload.size());
  n += payload.size();
  esp_err_t e = esp_ble_gap_config_adv_data_raw(adv, n);
  if (e != ESP_OK) return e;
  esp_ble_adv_params_t p = {};
  p.adv_int_min = 0x20;  // 20 ms
  p.adv_int_max = 0x20;
  p.adv_type = ADV_TYPE_IND;
  p.own_addr_type = BLE_ADDR_TYPE_PUBLIC;
  p.channel_map = ADV_CHNL_ALL;
  p.adv_filter_policy = ADV_FILTER_ALLOW_SCAN_ANY_CON_ANY;
  return esp_ble_gap_start_advertising(&p);
}

static void s2_stop_adv() { esp_ble_gap_stop_advertising(); }
