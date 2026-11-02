import {itemTextureUrl} from "./rest";

let atlasReady: Promise<void> | undefined;

/** Share one readiness probe across every registry row instead of retrying each broken image. */
export function whenItemTexturesReady(): Promise<void> {
    return atlasReady ??= (async () => {
        while (true) {
            try {
                const response = await fetch(itemTextureUrl("minecraft:grass_block"), {cache: "no-store"});
                if (response.ok) return;
            } catch {
                // The local API may not be ready yet.
            }
            await new Promise(resolve => setTimeout(resolve, 5000));
        }
    })();
}
