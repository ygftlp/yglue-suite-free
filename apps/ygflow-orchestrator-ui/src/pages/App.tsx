import React, { useEffect, useState } from 'react'

type Project = { id:number; key:string; name:string }
type Flow = { id:number; code:string; name:string }

const API = (path:string) => (import.meta.env.VITE_API_BASE ?? 'http://localhost:8090') + path

export default function App() {
  const [projects, setProjects] = useState<Project[]>([])
  const [selected, setSelected] = useState<Project|null>(null)
  const [flows, setFlows] = useState<Flow[]>([])

  useEffect(() => {
    fetch(API('/api/projects')).then(r => r.json()).then(setProjects)
  }, [])

  const openProject = async (p: Project) => {
    setSelected(p)
    const list = await fetch(API(`/api/projects/${p.key}/flows`)).then(r => r.json())
    setFlows(list)
  }

  return (
    <div style={{display:'grid', gridTemplateColumns:'280px 1fr', gap:16, padding:16, fontFamily:'system-ui'}}>
      <aside>
        <h2>项目</h2>
        <ul style={{listStyle:'none', padding:0}}>
          {projects.map(p =>
            <li key={p.id}>
              <button onClick={() => openProject(p)} style={{width:'100%', textAlign:'left', padding:'8px 12px'}}>
                <b>{p.name}</b><br/><small>{p.key}</small>
              </button>
            </li>
          )}
        </ul>
      </aside>
      <main>
        {selected ? (
          <div>
            <h2>{selected.name} <small>({selected.key})</small></h2>
            <h3>流程</h3>
            <table style={{width:'100%', borderCollapse:'collapse'}}>
              <thead>
                <tr><th style={{textAlign:'left'}}>标识</th><th style={{textAlign:'left'}}>名称</th></tr>
              </thead>
              <tbody>
                {flows.map(f =>
                  <tr key={f.id}>
                    <td>{f.code}</td>
                    <td>{f.name}</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        ) : <p>请选择左侧的项目。</p>}
      </main>
    </div>
  )
}
