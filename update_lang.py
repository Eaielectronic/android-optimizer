import json

new_keys = {
    "en_us": {
        "androidopt.config.ber_cull_distance": "BER cull distance (blocks)",
        "androidopt.config.fluid_cull_distance": "Fluid cull distance (blocks)",
        "androidopt.config.goggle_cull_distance": "Goggle overlay distance (blocks)",
        "androidopt.config.entity_throttle_distance": "Entity throttle distance (blocks)",
        "androidopt.config.tick_skip_distance": "BE tick skip distance (blocks)"
    },
    "fr_fr": {
        "androidopt.config.ber_cull_distance": "Distance cull BER (blocs)",
        "androidopt.config.fluid_cull_distance": "Distance cull fluides (blocs)",
        "androidopt.config.goggle_cull_distance": "Distance overlay Goggle (blocs)",
        "androidopt.config.entity_throttle_distance": "Distance throttle entités (blocs)",
        "androidopt.config.tick_skip_distance": "Distance skip tick BE (blocs)"
    },
    "de_de": {
        "androidopt.config.ber_cull_distance": "BER-Culling-Distanz (Blöcke)",
        "androidopt.config.fluid_cull_distance": "Flüssigkeits-Culling-Distanz (Blöcke)",
        "androidopt.config.goggle_cull_distance": "Goggle-Overlay-Distanz (Blöcke)",
        "androidopt.config.entity_throttle_distance": "Entitäts-Drosselungsdistanz (Blöcke)",
        "androidopt.config.tick_skip_distance": "BE-Tick-Skipdistanz (Blöcke)"
    },
    "es_es": {
        "androidopt.config.ber_cull_distance": "Distancia de cull BER (bloques)",
        "androidopt.config.fluid_cull_distance": "Distancia de cull fluidos (bloques)",
        "androidopt.config.goggle_cull_distance": "Distancia overlay Goggles (bloques)",
        "androidopt.config.entity_throttle_distance": "Distancia throttle entidades (bloques)",
        "androidopt.config.tick_skip_distance": "Distancia skip tick BE (bloques)"
    }
}

for lang, keys in new_keys.items():
    path = f"/home/ubuntu/android-optimizer/src/main/resources/assets/androidopt/lang/{lang}.json"
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data.update(keys)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

print("Language files updated with distance keys.")
