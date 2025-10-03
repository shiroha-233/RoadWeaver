*2.0.4*

Performance:

- **Road rendering LOD system**: Dramatically improved debug map performance
- Dynamic road detail adjustment based on zoom level (1x to 1000x performance boost)
- Roads now render with adaptive precision: full detail when zoomed in, simplified when zoomed out
- LOD levels: 300 blocks/grid (finest) → 500 (1/64) → 1000 (1/128) → 2000 (1/256) → 5000 (1/512) → 10000 (1/1024) → 15000+ (hidden)
- Supports 100+ roads with 10,000+ segments each without performance degradation

***

*2.0.3*

Feature:

- **Unlimited structure discovery**: Removed max structure count limit (`maxLocatingCount`)
- New config: `structureSearchRadius` - Adjustable search radius (50-200 chunks, default: 100)
- Structures now continue to be discovered as players explore, without arbitrary limits
- Players have full freedom to connect any structures they discover

Fix:

- UI: Reduced structure node size when zoomed in (logarithmic scaling)

***

*2.0.2*

Fix:

- **Road persistence**: Unfinished roads now continue generating after reloading the world
- Automatically restore PLANNED and GENERATING connections on world load
- Reset interrupted GENERATING tasks to PLANNED state for proper recovery

***

*2.0.1*

Note: When updating from the previous version make sure to delete (or reset) the old midnightlib config. Otherwise no roads will be generated

***
Fix: 

- Small road averaging improvement
